/*
 * This file is part of Popcorn Time.
 *
 * Popcorn Time is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Popcorn Time is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Popcorn Time. If not, see <http://www.gnu.org/licenses/>.
 */

package pct.droid.tv.fragments;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import hugo.weaving.DebugLog;
import pct.droid.base.providers.media.EZTVProvider;
import pct.droid.base.providers.media.MediaProvider;
import pct.droid.base.providers.media.YTSProvider;
import pct.droid.base.providers.media.models.Media;
import pct.droid.base.providers.media.models.Movie;
import pct.droid.base.providers.subs.SubsProvider;
import pct.droid.base.providers.subs.YSubsProvider;
import pct.droid.base.torrent.StreamInfo;
import pct.droid.base.utils.ThreadUtils;
import pct.droid.tv.BuildConfig;
import pct.droid.tv.R;
import pct.droid.tv.activities.PTVMediaDetailActivity;
import pct.droid.tv.activities.PTVMediaGridActivity;
import pct.droid.tv.activities.PTVPreferencesActivity;
import pct.droid.tv.activities.PTVSearchActivity;
import pct.droid.tv.activities.PTVVideoPlayerActivity;
import pct.droid.tv.presenters.MediaCardPresenter;
import pct.droid.tv.presenters.MorePresenter;
import pct.droid.tv.utils.BackgroundUpdater;

/*
 * Main class to show BrowseFragment with header and rows of videos
 */
public class PTVOverviewFragment extends BrowseFragment implements OnItemViewClickedListener, OnItemViewSelectedListener {

    private Integer mSelectedRow = 0;

    private ArrayObjectAdapter mRowsAdapter;
    private ArrayObjectAdapter mShowAdapter;
    private ArrayObjectAdapter mMoviesAdapter;

    private YTSProvider mMoviesProvider = new YTSProvider();
    private EZTVProvider mShowsProvider = new EZTVProvider();

    private BackgroundUpdater mBackgroundUpdater;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //setup background updater
        mBackgroundUpdater = new BackgroundUpdater();
        mBackgroundUpdater.initialise(getActivity(), R.color.black);

        //setup main adapter
        ListRowPresenter mainMenuRowPresenter = new ListRowPresenter();
        mainMenuRowPresenter.setShadowEnabled(false);
        mRowsAdapter = new ArrayObjectAdapter(mainMenuRowPresenter);
        setAdapter(mRowsAdapter);

        setupUIElements();
        setupEventListeners();
        setupAdapters();
        loadData();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != mBackgroundUpdater) mBackgroundUpdater.destroy();
    }

    @Override
    public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        if (item instanceof MediaCardPresenter.MediaCardItem) {
            onMediaItemClicked((MediaCardPresenter.CustomImageCardView) itemViewHolder.view, (MediaCardPresenter.MediaCardItem) item);
        } else if (item instanceof MorePresenter.MoreItem) {
            onMoreItemClicked((MorePresenter.MoreItem) item);
        }
    }

    @Override
    public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        mSelectedRow = mRowsAdapter.indexOf(row);

        if (item instanceof MediaCardPresenter.MediaCardItem) {
            MediaCardPresenter.MediaCardItem overviewItem = (MediaCardPresenter.MediaCardItem) item;
            if (overviewItem.isLoading()) return;
            mBackgroundUpdater.updateBackgroundAsync(((MediaCardPresenter.MediaCardItem) item).getMedia().headerImage);
        }
    }

    private void setupUIElements() {
        setBadgeDrawable(ActivityCompat.getDrawable(getActivity(), R.drawable.header_logo));
        setTitle(getString(R.string.app_name)); // Badge, when set, takes precedent over title
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);
        // set fastLane (or headers) background color
        setBrandColor(ActivityCompat.getColor(getActivity(), R.color.primary));
        // set search icon color
        setSearchAffordanceColor(ActivityCompat.getColor(getActivity(), R.color.primary_dark));
    }

    private void loadData() {
        final MediaProvider.Filters showsFilter = new MediaProvider.Filters();
        showsFilter.sort = MediaProvider.Filters.Sort.DATE;
        showsFilter.order = MediaProvider.Filters.Order.DESC;

        mShowsProvider.getList(null, showsFilter, new MediaProvider.Callback() {
            @DebugLog
            @Override
            public void onSuccess(MediaProvider.Filters filters, ArrayList<Media> items, boolean changed) {
                List<MediaCardPresenter.MediaCardItem> list = MediaCardPresenter.convertMediaToOverview(items);
                mShowAdapter.clear();
                mShowAdapter.addAll(0, list);

                if(mSelectedRow == 1)
                    mBackgroundUpdater.updateBackgroundAsync(items.get(0).headerImage);
            }

            @DebugLog
            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
                ThreadUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getActivity(), R.string.encountered_error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        final MediaProvider.Filters movieFilters = new MediaProvider.Filters();
        movieFilters.sort = MediaProvider.Filters.Sort.POPULARITY;
        movieFilters.order = MediaProvider.Filters.Order.DESC;

        mMoviesProvider.getList(null, movieFilters, new MediaProvider.Callback() {
            @DebugLog
            @Override
            public void onSuccess(MediaProvider.Filters filters, ArrayList<Media> items, boolean changed) {
                List<MediaCardPresenter.MediaCardItem> list = MediaCardPresenter.convertMediaToOverview(items);
                mMoviesAdapter.clear();
                mMoviesAdapter.addAll(0, list);

                if(mSelectedRow == 0)
                    mBackgroundUpdater.updateBackgroundAsync(items.get(0).headerImage);
            }

            @DebugLog
            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
                ThreadUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getActivity(), R.string.movies_error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void setupEventListeners() {
        setOnSearchClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PTVSearchActivity.startActivity(getActivity());
            }
        });

        setOnItemViewClickedListener(this);
        setOnItemViewSelectedListener(this);
    }

    private void setupAdapters() {
        setupMovies();
        setupTVShows();
        setupMoreMovies();
        setupMoreTVShows();
        setupMore();
    }

    private void setupMovies() {
        HeaderItem moviesHeader = new HeaderItem(0, getString(R.string.top_movies));
        MediaCardPresenter mediaCardPresenter = new MediaCardPresenter(getActivity());
        mMoviesAdapter = new ArrayObjectAdapter(mediaCardPresenter);
        mMoviesAdapter.add(new MediaCardPresenter.MediaCardItem(true));
        mRowsAdapter.add(new ListRow(moviesHeader, mMoviesAdapter));
    }

    private void setupMoreMovies() {
        HeaderItem moreMoviesHeader = new HeaderItem(1, getString(R.string.more_movies));
        MorePresenter morePresenter = new MorePresenter(getActivity());
        ArrayObjectAdapter moreRowAdapter = new ArrayObjectAdapter(morePresenter);

        // add items
        List<MediaProvider.NavInfo> navigation = mMoviesProvider.getNavigation();
        for (MediaProvider.NavInfo info : navigation) {
            moreRowAdapter.add(new MorePresenter.MoreItem(
                    info.getId(),
                    info.getLabel(),
                    info.getIcon(),
                    info));
        }

        mRowsAdapter.add(new ListRow(moreMoviesHeader, moreRowAdapter));
    }

    private void setupTVShows() {
        HeaderItem showsHeader = new HeaderItem(0, getString(R.string.latest_shows));
        MediaCardPresenter mediaCardPresenter = new MediaCardPresenter(getActivity());
        mShowAdapter = new ArrayObjectAdapter(mediaCardPresenter);
        mShowAdapter.add(new MediaCardPresenter.MediaCardItem(true));
        mRowsAdapter.add(new ListRow(showsHeader, mShowAdapter));
    }

    private void setupMoreTVShows() {
        HeaderItem moreHeader = new HeaderItem(1, getString(R.string.more_shows));
        MorePresenter morePresenter = new MorePresenter(getActivity());
        ArrayObjectAdapter moreRowAdapter = new ArrayObjectAdapter(morePresenter);

        // add items
        List<MediaProvider.NavInfo> navigation = mShowsProvider.getNavigation();
        for (MediaProvider.NavInfo info : navigation) {
            moreRowAdapter.add(new MorePresenter.MoreItem(
                    info.getId(),
                    info.getLabel(),
                    info.getIcon(),
                    info));
        }

        mRowsAdapter.add(new ListRow(moreHeader, moreRowAdapter));
    }

    private void setupMore() {
        HeaderItem gridHeader = new HeaderItem(0, getString(R.string.more));
        MorePresenter gridPresenter = new MorePresenter(getActivity());
        ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(gridPresenter);
        if (BuildConfig.DEBUG) {
            gridRowAdapter.add(new MorePresenter.MoreItem(R.id.more_player_tests, getString(R.string.tests), R.drawable.more_player_tests, null));
        }
        gridRowAdapter.add(new MorePresenter.MoreItem(R.id.more_item_settings, getString(R.string.preferences), R.drawable.ic_settings, null));
        mRowsAdapter.add(new ListRow(gridHeader, gridRowAdapter));
    }

    private void onMediaItemClicked(MediaCardPresenter.CustomImageCardView view, MediaCardPresenter.MediaCardItem media) {
        if (media.isLoading()) return;
        Bundle options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                getActivity(),
                view.getMainImageView(),
                PTVMediaDetailActivity.SHARED_ELEMENT_NAME).toBundle();

        Media mediaItem = media.getMedia();
        mediaItem.color = view.getCustomSelectedSwatch().getRgb();

        PTVMediaDetailActivity.startActivity(
                getActivity(),
                options,
                mediaItem);
    }

    private void onMoreItemClicked(MorePresenter.MoreItem moreItem) {
        switch (moreItem.getId()) {
            case R.id.more_player_tests:
                openPlayerTestDialog();
                break;
            case R.id.more_item_settings:
                PTVPreferencesActivity.startActivity(getActivity());
                break;
            case R.id.yts_filter_a_to_z:
            case R.id.yts_filter_trending:
            case R.id.yts_filter_release_date:
            case R.id.yts_filter_popular_now:
            case R.id.yts_filter_year:
            case R.id.yts_filter_top_rated:
                PTVMediaGridActivity.startActivity(getActivity(), moreItem.getNavInfo().getLabel(), PTVMediaGridActivity.ProviderType.MOVIE, moreItem.getNavInfo().getFilter(), moreItem.getNavInfo().getOrder(), null);
                break;
            case R.id.eztv_filter_a_to_z:
            case R.id.eztv_filter_trending:
            case R.id.eztv_filter_last_updated:
            case R.id.eztv_filter_popular_now:
            case R.id.eztv_filter_year:
            case R.id.eztv_filter_top_rated:
                PTVMediaGridActivity.startActivity(getActivity(), moreItem.getNavInfo().getLabel(), PTVMediaGridActivity.ProviderType.SHOW, moreItem.getNavInfo().getFilter(), moreItem.getNavInfo().getOrder(), null);
                break;
            case R.id.yts_filter_genres:
                Toast.makeText(getActivity(), "Not implemented yet", Toast.LENGTH_LONG).show();
                break;
        }
    }

    private void openPlayerTestDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        final String[] file_types = getResources().getStringArray(R.array.file_types);
        final String[] files = getResources().getStringArray(R.array.files);

        builder.setTitle("Player Tests")
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                }).setSingleChoiceItems(file_types, -1, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int index) {
                dialogInterface.dismiss();
                final String location = files[index];
                if (location.equals("dialog")) {
                    final EditText dialogInput = new EditText(getActivity());
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                            .setView(dialogInput)
                            .setPositiveButton("Start", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Movie media = new Movie(new YTSProvider(), new YSubsProvider());

                                    media.videoId = "dialogtestvideo";
                                    media.title = "User input test video";

                                    PTVVideoPlayerActivity.startActivity(getActivity(), new StreamInfo(media, null, null, null, null, location), 0);
                                }
                            });
                    builder.show();
                }

                final Movie media = new Movie(new YTSProvider(), new YSubsProvider());
                media.videoId = "bigbucksbunny";
                media.title = file_types[index];
                media.subtitles = new HashMap<>();
                media.subtitles.put("en", "http://sv244.cf/bbb-subs.srt");

                SubsProvider.download(getActivity(), media, "en", new Callback() {
                    @Override
                    public void onFailure(Request request, IOException e) {
                        PTVVideoPlayerActivity.startActivity(getActivity(), new StreamInfo(media, null, null, null, null, location));
                    }

                    @Override
                    public void onResponse(Response response) throws IOException {
                        PTVVideoPlayerActivity.startActivity(getActivity(), new StreamInfo(media, null, null, null, null, location));
                    }
                });
            }
        });

        builder.show();
    }
}
